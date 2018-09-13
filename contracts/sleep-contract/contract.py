import json
import time
import sys

if __name__ == '__main__':
    request = json.loads(sys.argv[1])
    if request['command'] == 'execute':
        if not 'execute_sleep' in request['params']:
            sys.exit(-1)

        time.sleep(request['params']['execute_sleep'])
    elif request['command'] == 'apply':
        if not 'apply_sleep' in request['params']:
            sys.exit(-1)

        time.sleep(request['params']['apply_sleep'])
    elif request['command'] == 'init':
        if not 'init_sleep' in request['params']:
            sys.exit(-1)

        time.sleep(request['params']['init_sleep'])
    else:
        sys.exit(-1)