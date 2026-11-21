#!/usr/bin/env python3
"""Deploy only successful master-push CI images; never execute repository scripts."""
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.request

REPO = 'goodbyeO0/chubb-claims'
WORKFLOW = 'ci.yml'
ROOT = Path('/home/izhan/Projects/Chubb/readme_main_impl')
STATE_DIR = Path.home() / '.local/state/chubb-deploy'
DOCKER = ['docker', '--context', 'desktop-linux']
LIVE_IMAGE = 'chubb-review-api:latest'


def run(args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)


def output(args):
    return run(args, stdout=subprocess.PIPE).stdout.strip()


def api(path):
    return json.loads(output(['gh', 'api', f'repos/{REPO}/{path}']))


def eligible(run_info, head):
    return (run_info.get('event') == 'push'
            and run_info.get('head_branch') == 'master'
            and run_info.get('head_sha') == head
            and run_info.get('status') == 'completed'
            and run_info.get('conclusion') == 'success'
            and run_info.get('head_repository', {}).get('full_name') == REPO)


def healthy():
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen('http://127.0.0.1:18080/actuator/health', timeout=5) as response:
                if json.load(response).get('status') == 'UP':
                    return True
        except (OSError, ValueError):
            pass
        time.sleep(2)
    return False


def activate(image):
    run(DOCKER + ['tag', image, LIVE_IMAGE])
    # Operational settings and secrets remain local. Only the API image changes.
    env = {**os.environ, 'APP_IMAGE': LIVE_IMAGE}
    run(DOCKER + ['compose', '-p', 'chubb-review', 'up', '--no-deps',
                  '--no-build', '--pull', 'never', '-d', 'api'], cwd=ROOT, env=env)
    if not healthy():
        raise RuntimeError('New app failed its health check')


def save_state(value):
    temporary = STATE_DIR / 'state.tmp'
    temporary.write_text(json.dumps(value, indent=2) + '\n')
    temporary.replace(STATE_DIR / 'state.json')


def main():
    STATE_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (STATE_DIR / 'deploy.lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return
        # Do not restart an app that the operator deliberately stopped.
        active = subprocess.run(['systemctl', '--user', 'is-active', '--quiet', 'chubb-app'])
        if active.returncode:
            return
        head = api('commits/master')['sha']
        if not re.fullmatch(r'[0-9a-f]{40}', head):
            raise RuntimeError('Invalid commit SHA')
        state_path = STATE_DIR / 'state.json'
        state = json.loads(state_path.read_text()) if state_path.exists() else {}
        if state.get('sha') == head:
            return
        runs = api(f'actions/workflows/{WORKFLOW}/runs?branch=master&event=push&head_sha={head}&per_page=10')['workflow_runs']
        if not runs:
            return
        candidate = max(runs, key=lambda item: (item['run_number'], item.get('run_attempt', 1)))
        if not eligible(candidate, head):
            return
        run_id = candidate['id']
        # Failed candidates stay rolled back until a new commit or explicit rerun.
        attempt = candidate.get('run_attempt', 1)
        if state.get('failed') == [run_id, attempt]:
            return
        artifacts = api(f'actions/runs/{run_id}/artifacts')['artifacts']
        name = f'deploy-image-{head}'
        if not any(a['name'] == name and not a['expired'] for a in artifacts):
            return
        previous = output(DOCKER + ['inspect', 'chubb-review-api-1', '--format', '{{.Image}}'])
        print(f'Downloading tested image for {head}, CI run {run_id}', flush=True)
        with tempfile.TemporaryDirectory(prefix='chubb-deploy-') as directory:
            run(['gh', 'run', 'download', str(run_id), '--repo', REPO,
                 '--name', name, '--dir', directory])
            run(DOCKER + ['load', '--input', str(Path(directory) / 'chubb-image.tar.gz')])
        image = f'chubb-app:{head}'
        output(DOCKER + ['image', 'inspect', image, '--format', '{{.Id}}'])
        # A newer push during download must not be replaced by this older build.
        if api('commits/master')['sha'] != head:
            print('A newer master commit exists; skipping stale deployment.', flush=True)
            return
        try:
            activate(image)
        except Exception:
            print('Deployment failed; restoring the previous image.', flush=True)
            activate(previous)
            state['failed'] = [run_id, attempt]
            save_state(state)
            raise
        save_state({'sha': head, 'run_id': run_id, 'previous_image': previous,
                    'deployed_at': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())})
        print(f'Deployed {head}; health UP. Previous image retained for rollback.', flush=True)


if __name__ == '__main__':
    main()
