"""Exercise a running local-demo API. Uses only the Python standard library."""
import argparse
from http.cookiejar import CookieJar
from datetime import date, timedelta
import json
from urllib.error import HTTPError
from urllib.parse import urlencode, unquote, urlsplit
from urllib.request import build_opener, HTTPCookieProcessor, Request

class Browser:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.jar = CookieJar()
        self.client = build_opener(HTTPCookieProcessor(self.jar))

    def call(self, path, method='GET', body=None, expected=200, form=False, csrf=True):
        headers = {'Accept': 'application/json'}
        data = None
        if method == 'POST':
            data = b''
            if csrf:
                token = next(cookie.value for cookie in self.jar if cookie.name == 'XSRF-TOKEN')
                headers['X-XSRF-TOKEN'] = unquote(token)
            if body is not None:
                headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
                data = (urlencode(body) if form else json.dumps(body)).encode()
        req = Request(self.base + path, data=data, headers=headers, method=method)
        try:
            response = self.client.open(req, timeout=10)
        except HTTPError as error:
            response = error
        with response:
            raw = response.read()
            if response.status != expected:
                raise RuntimeError(f'{method} {path}: expected {expected}, got {response.status}')
            if expected == 204:
                if raw:
                    raise ValueError('Unexpected response body on 204')
                return None
            if response.headers.get_content_type() != 'application/json':
                raise ValueError('Expected application/json response')
            return json.loads(raw)

    def login(self, username):
        self.call('/api/auth/csrf', expected=204)
        self.call('/api/auth/login','POST',{'username':username,'password':'local-demo-only'},204,form=True)
        self.call('/api/auth/csrf', expected=204)
        return self.call('/api/auth/me')

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base', default='http://localhost:8080')
    p.add_argument('--through', choices=['auth','intake','handoff','decision'], default='decision')
    a = p.parse_args()
    if urlsplit(a.base).hostname not in ('localhost','127.0.0.1','::1'):
        p.error('Use a local-demo origin. This command creates demo claims.')
    aina, daniel, farah = [Browser(a.base) for _ in range(3)]
    assert aina.call('/api/auth/me',expected=401)['code']=='UNAUTHENTICATED'
    assert aina.login('aina')['role']=='CLAIMANT'
    assert daniel.login('daniel')['role']=='CLAIMANT'
    assert farah.login('farah')['role']=='OFFICER'
    if a.through=='auth':
        aina.call('/api/auth/logout','POST',expected=204)
        aina.call('/api/auth/me',expected=401)
        return
    body={'kind':'MOTOR','market':'MY','incidentOn':str(date.today()-timedelta(days=1)),'description':'Local smoke test bumper damage.'}
    assert aina.call('/api/claims','POST',body,403,csrf=False)['code']=='CSRF_INVALID'
    claim=aina.call('/api/claims','POST',body,201)
    assert claim['status']=='SUBMITTED' and claim['version']==1 and claim['currency']=='MYR'
    own='/api/claims/'+claim['id']
    staff='/api/staff/claims/'+claim['id']
    assert 'reserve' not in aina.call(own)
    assert daniel.call(own,expected=404)['code']=='NOT_FOUND'
    if a.through=='intake':return
    assigned=farah.call(staff+'/assign','POST',{'expectedVersion':claim['version']})
    asked=farah.call(staff+'/requests','POST',{'expectedVersion':assigned['version'],'question':'What is the repair estimate?'})
    assert asked['status']=='AWAITING_INFO'
    req=asked['requests'][-1]['id']
    answered=aina.call(own+'/requests/'+req+'/answer','POST',{'expectedVersion':asked['version'],'answer':'MYR 5,000.'})
    assert answered['status']=='UNDER_REVIEW' and answered['requests'][-1]['answeredAt']
    if a.through=='handoff':return
    reserved=farah.call(staff+'/reserve','POST',{'expectedVersion':answered['version'],'amount':'5000.00'})
    assert reserved['reserve']=='5000.00'
    assert farah.call(staff+'/settle','POST',{'expectedVersion':answered['version'],'amount':'4200.00'},409)['code']=='VERSION_CONFLICT'
    settled=farah.call(staff+'/settle','POST',{'expectedVersion':reserved['version'],'amount':'4200.00'})
    assert settled['status']=='SETTLED' and settled['reserve']=='0.00'
    public=aina.call(own)
    assert public['settlement']=='4200.00' and 'reserve' not in public
    assert all(row['type']!='RESERVE_SET' for row in public['timeline'])

if __name__ == '__main__':
    main()
    print('Selected live API smoke checks passed.')
