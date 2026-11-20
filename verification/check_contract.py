"""Run from any directory. Requires PyYAML, jsonschema[format], openapi-spec-validator."""
import argparse
import json
from pathlib import Path
import yaml
from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import validate

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--refresh-fixtures', action='store_true')
args = parser.parse_args()
spec = yaml.safe_load((root / 'api/openapi.yaml').read_text())
validate(spec)
fixtures = {}
seen = set()
count = 0

def check(media):
    global count
    schema = {'components': spec['components'], **media['schema']}
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    examples = media['examples']
    for example in examples.values():
        validator.validate(example['value'])
        count += 1
    return {name: example['value'] for name, example in examples.items()}

for path, methods in spec['paths'].items():
    for method, operation in methods.items():
        operation_id = operation['operationId']
        if operation_id in seen:
            raise ValueError(f'Duplicate operation {operation_id}')
        seen.add(operation_id)
        for media in operation.get('requestBody', {}).get('content', {}).values():
            check(media)
        for status, response in operation['responses'].items():
            for media in response.get('content', {}).values():
                examples = check(media)
                if status.startswith('2'):
                    fixtures[operation_id] = examples

event = json.loads((root / 'api/exposure-event.schema.json').read_text())
Draft202012Validator.check_schema(event)
for example in event['examples']:
    Draft202012Validator(event, format_checker=FormatChecker()).validate(example)

fixture_path = root / 'api/ui-fixtures.json'
if args.refresh_fixtures:
    fixture_path.write_text(json.dumps(fixtures, indent=2) + '\n')
elif fixtures != json.loads(fixture_path.read_text()):
    raise ValueError('Fixture drift. Review the contract and use --refresh-fixtures.')
print(f'OK: {len(seen)} operations, {count} HTTP examples, Kafka example, UI fixtures.')
