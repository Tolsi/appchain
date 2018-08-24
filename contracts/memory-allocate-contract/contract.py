from flask import Flask, abort, request
import json
import time

app = Flask(__name__)


@app.route('/execute', methods=['POST'])
def execute():
    if not request.json or not 'allocate' in request.json:
        abort(400)
    global omfg
    omfg = bytearray(request.json['allocate'])

    time.sleep(2)

    return json.dumps(True)

@app.route('/apply', methods=['POST'])
def apply():
    if not request.json or not 'allocate' in request.json['parameters']:
        abort(400)

    global omfg
    omfg = bytearray(request.json['parameters']['allocate'])

    time.sleep(2)

    return json.dumps(True)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)