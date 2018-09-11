import json
import sys

if __name__ == '__main__':
    request = json.loads(sys.argv[1])
    if request['command'] == 'execute':
        if not request['params'] or not 'a' in request['params'] or not 'b' in request['params']:
            sys.exit(1)
        print(json.dumps(request['params']['a'] + request['params']['b']))
    elif request['command'] == 'apply':
        if not request['params'] or not 'a' in request['params'] or not 'b' in request['params']:
            sys.exit(1)

        a = request['params']['a']
        b = request['params']['b']
        res = request['params']['result']

        if not a + b == res:
            sys.exit(1)