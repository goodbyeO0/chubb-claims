import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock

spec = importlib.util.spec_from_file_location('deploy', Path(__file__).with_name('auto-deploy.py'))
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)
SHA = 'a' * 40


def successful():
    return {'id': 42, 'run_number': 3, 'run_attempt': 1, 'event': 'push',
            'head_branch': 'master', 'head_sha': SHA, 'status': 'completed',
            'conclusion': 'success', 'head_repository': {'full_name': deploy.REPO}}


class DeploymentTests(unittest.TestCase):
    def test_only_successful_same_repository_master_push_is_eligible(self):
        self.assertTrue(deploy.eligible(successful(), SHA))
        for changes in ({'event': 'pull_request'}, {'head_branch': 'feature'},
                        {'head_sha': 'b' * 40}, {'conclusion': 'failure'},
                        {'status': 'in_progress'}, {'head_repository': {'full_name': 'fork/repo'}}):
            self.assertFalse(deploy.eligible({**successful(), **changes}, SHA))

    def scenario(self, activate, newer_head=False, state=None):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            if state:
                (path / 'state.json').write_text(json.dumps(state))
            api = Mock(side_effect=[{'sha': SHA}, {'workflow_runs': [successful()]},
                                   {'artifacts': [{'name': f'deploy-image-{SHA}', 'expired': False}]},
                                   {'sha': 'b' * 40 if newer_head else SHA}])
            with patch.object(deploy, 'STATE_DIR', path), patch.object(deploy, 'api', api), \
                 patch.object(deploy.subprocess, 'run', return_value=Mock(returncode=0)), \
                 patch.object(deploy, 'run'), patch.object(deploy, 'output', return_value='previous-image'), \
                 patch.object(deploy, 'activate', activate):
                try:
                    deploy.main()
                except RuntimeError:
                    pass
            saved = json.loads((path / 'state.json').read_text()) if (path / 'state.json').exists() else {}
            return saved

    def test_success_records_deployed_revision_and_previous_image(self):
        activate = Mock()
        state = self.scenario(activate)
        activate.assert_called_once_with(f'chubb-app:{SHA}')
        self.assertEqual(state['sha'], SHA)
        self.assertEqual(state['previous_image'], 'previous-image')

    def test_failed_health_restores_previous_image_and_records_failure(self):
        activate = Mock(side_effect=[RuntimeError('health failed'), None])
        state = self.scenario(activate)
        self.assertEqual([call.args[0] for call in activate.call_args_list],
                         [f'chubb-app:{SHA}', 'previous-image'])
        self.assertEqual(state['failed'], [42, 1])
        self.assertNotIn('sha', state)

    def test_newer_push_during_download_does_not_activate_stale_image(self):
        activate = Mock()
        self.scenario(activate, newer_head=True)
        activate.assert_not_called()

    def test_already_deployed_revision_is_not_restarted(self):
        activate = Mock()
        self.scenario(activate, state={'sha': SHA})
        activate.assert_not_called()

    def test_failed_attempt_is_not_retried_each_minute(self):
        activate = Mock()
        self.scenario(activate, state={'failed': [42, 1]})
        activate.assert_not_called()


if __name__ == '__main__':
    unittest.main()
