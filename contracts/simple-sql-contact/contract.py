import json
import psycopg2
import sys

if __name__ == '__main__':
    request = json.loads(sys.argv[1])
    if request['command'] == 'execute':
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
        print(json.dumps(result))
    elif request['command'] == 'apply':
        if not request.json or request.json['result'] != 1:
            sys.exit(1)
        print(json.dumps(True))
    elif request['command'] == 'init':
        sys.exit(0)
    else:
        sys.exit(1)