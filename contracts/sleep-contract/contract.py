from flask import Flask, abort, request
import json
import time

app = Flask(__name__)

@app.route('/execute', methods=['POST'])
def execute():
    if not request.json or not 'execute_sleep' in request.json or not 'apply_sleep' in request.json:
        abort(400)
    time.sleep(request.json['execute_sleep'])
    return json.dumps(True)

@app.route('/apply', methods=['POST'])
def apply():
    if not request.json or not 'execute_sleep' in request.json['parameters'] or not 'apply_sleep' in request.json['parameters']:
        abort(400)

    time.sleep(request.json['parameters']['apply_sleep'])

    return json.dumps(True)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)