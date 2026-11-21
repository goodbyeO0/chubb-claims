"""Run every contract operation against a local API and validate response schemas."""
import argparse
import re
from pathlib import Path
from datetime import date, timedelta
import yaml
from jsonschema import Draft202012Validator, RefResolver, FormatChecker
from api_smoke import Browser

SPEC = yaml.safe_load((Path(__file__).parents[1] / 'api/openapi.yaml').read_text())
RESOLVER = RefResolver.from_schema(SPEC)
SEEN = set()

class ContractBrowser(Browser):
    def call(self, path, method='GET', body=None, expected=200, form=False, csrf=True):
        value = super().call(path, method, body, expected, form, csrf)
        matched = False
        for template, methods in SPEC['paths'].items():
            pattern = re.sub(r'\{[^}]+\}', '[^/]+', template)
            if re.fullmatch(pattern, path.split('?')[0]) and method.lower() in methods:
                operation = methods[method.lower()]
                response = operation['responses'][str(expected)]
                if expected != 204:
                    schema = response['content']['application/json']['schema']
                    Draft202012Validator(schema, resolver=RESOLVER, format_checker=FormatChecker()).validate(value)
                SEEN.add(operation['operationId'])
                matched = True
                break
        assert matched, (method, path)
        return value

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='http://localhost:8080')
    args = parser.parse_args()
    from urllib.parse import urlsplit
    if urlsplit(args.base).hostname not in ('localhost', '127.0.0.1', '::1'):
        parser.error('Only a local demo API is permitted.')
    aina, daniel, farah, arjun = [ContractBrowser(args.base) for _ in range(4)]
    aina.call('/actuator/health')
    aina.call('/api/auth/me', expected=401)
    for browser, name in [(aina,'aina'),(daniel,'daniel'),(farah,'farah'),(arjun,'arjun')]:
        browser.login(name)
        session = next(cookie for cookie in browser.jar if cookie.name == 'JSESSIONID')
        assert session.has_nonstandard_attr('HttpOnly')
        assert session.get_nonstandard_attr('SameSite') == 'Lax'
    payload = dict(kind='MOTOR',market='MY',incidentOn=str(date.today()-timedelta(days=1)),description='Contract verification incident.')
    aina.call('/api/claims','POST',{**payload,'claimantId':'forged'},400)
    claim = aina.call('/api/claims','POST',payload,201)
    own = '/api/claims/' + claim['id']
    staff = '/api/staff/claims/' + claim['id']
    aina.call('/api/claims')
    aina.call(own)
    daniel.call(own,expected=404)
    farah.call('/api/staff/claims?scope=UNASSIGNED')
    farah.call(staff)
    claim = farah.call(staff+'/assign','POST',dict(expectedVersion=1))
    claim = farah.call(staff+'/requests','POST',dict(expectedVersion=claim['version'],question='What is the estimate?'))
    claim = aina.call(own+'/requests/'+claim['requests'][-1]['id']+'/answer','POST',dict(expectedVersion=claim['version'],answer='MYR 5000.00.'))
    claim = farah.call(staff+'/reserve','POST',dict(expectedVersion=claim['version'],amount='5000.00'))
    claim = farah.call(staff+'/settle','POST',dict(expectedVersion=claim['version'],amount='4200.00'))
    assert aina.call(own)['settlement'] == '4200.00'
    claim = daniel.call('/api/claims','POST',{**payload,'kind':'PROPERTY','market':'SG'},201)
    staff = '/api/staff/claims/' + claim['id']
    claim = arjun.call(staff+'/assign','POST',dict(expectedVersion=1))
    claim = arjun.call(staff+'/reject','POST',dict(expectedVersion=claim['version'],reason='Duplicate confirmed by claimant.'))
    assert daniel.call('/api/claims/'+claim['id'])['timeline'][-1]['message'] == 'Duplicate confirmed by claimant.'
    farah.call('/api/staff/reports/workload')
    farah.call('/api/staff/reports/exposure')
    aina.call('/api/auth/logout','POST',expected=204)
    aina.call('/api/auth/me',expected=401)
    assert len(SEEN) == 18, SEEN
    print('PASS: all 18 operations, schema-valid live responses, both decisions and owner isolation.')

if __name__ == '__main__':
    main()
