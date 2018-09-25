import json
import sys

if __name__ == '__main__':
    request = json.loads(sys.argv[1])
    if request['command'] == 'execute':
        if not request['params'] or not 'a' in request['params'] or not 'b' in request['params']:
            sys.exit(-1)
        print(json.dumps([{
            "key": '{0}+{1}'.format(request['params']['a'], request['params']['b']),
            "type": "integer",
            "value": request['params']['a'] + request['params']['b']}], separators=(',', ':')))
    elif request['command'] == 'init':
        sys.exit(0)
    else:
        sys.exit(-1)