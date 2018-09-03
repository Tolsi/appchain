from flask import Flask, abort, request
import json
import psycopg2

app = Flask(__name__)

@app.route('/execute', methods=['POST'])
def execute():
    """ Connect to the PostgreSQL database server """
    # connect to the PostgreSQL server
    print('Connecting to the PostgreSQL database...')
    conn = psycopg2.connect("dbname='postgres' user='postgres' host='state' port=5432 password='postgres'")

    # create a cursor
    cur = conn.cursor()

    # execute a statement
    print('PostgreSQL database version:')
    cur.execute('SELECT version()')

    # display the PostgreSQL database server version
    db_version = cur.fetchone()
    print(db_version)
    result = db_version
    conn.close()
    print('Database connection closed.')
    return json.dumps(result)

@app.route('/apply', methods=['POST'])
def apply():
    if not request.json or request.json['result'] != 1:
        abort(400)
    return json.dumps(True)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)