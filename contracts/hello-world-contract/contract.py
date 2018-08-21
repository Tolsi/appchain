from flask import Flask, abort, request
import json

app = Flask(__name__)

@app.route('/execute', methods=['POST'])
def execute():
    if not request.json or not 'a' in request.json or not 'b' in request.json:
        abort(400)
    return json.dumps(request.json['a'] + request.json['b'])

@app.route('/apply', methods=['POST'])
def apply():
    if not request.json or not 'a' in request.json['parameters'] or not 'b' in request.json['parameters']:
        abort(400)

    a = request.json['parameters']['a']
    b = request.json['parameters']['b']
    res = request.json['result']

    if not a + b == res:
        abort(400)

    return ""

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)