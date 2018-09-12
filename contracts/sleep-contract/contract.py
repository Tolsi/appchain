import json
import time
import sys

if __name__ == '__main__':
    request = json.loads(sys.argv[1])
    if request['command'] == 'execute':
        if not 'execute_sleep' in request['params'] or not 'apply_sleep' in request['params']:
            sys.exit(1)

        time.sleep(request['params']['execute_sleep'])

        print(json.dumps(True))
    elif request['command'] == 'apply':
        if not 'execute_sleep' in request['params'] or not 'apply_sleep' in request['params']:
            sys.exit(1)

        time.sleep(request['params']['apply_sleep'])

        print(json.dumps(True))
    elif request['command'] == 'init':
        sys.exit(0)
    else:
        sys.exit(1)