import json
import time
import sys

if __name__ == '__main__':
    request = json.loads(sys.argv[1])
    if request['command'] == 'execute':
        if not request['params'] or not 'allocate' in request['params']:
            sys.exit(400)
        global omfg
        omfg = bytearray(request['params']['allocate'])

        time.sleep(2)

        print(json.dumps(True))
    elif request['command'] == 'apply':
        if not request['params'] or not 'allocate' in request['params']:
            sys.exit(400)

        global omfg
        omfg = bytearray(request['params']['allocate'])

        time.sleep(2)

        print(json.dumps(True))
    elif request['command'] == 'init':
        sys.exit(0)
    else:
        sys.exit(1)