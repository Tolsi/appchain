import json
import psycopg2
import sys
import os
from subprocess import Popen, PIPE

def run_process_with_output(cmd):
    process = Popen(cmd.split(), stdout=PIPE)
    out, err = process.communicate()
    exit_code = process.wait()
    return (exit_code, out)

def run_process(cmd):
    process = Popen(cmd.split())
    exit_code = process.wait()
    return exit_code

def write_db_to_file():
    return run_process("pg_dump -U postgres -h state postgres -f /tmp/db.sql") + run_process("sed -i -e /^--/d -e /^$/d /tmp/db.sql")

def xxhash_db_file():
    exit_code, out = run_process_with_output("xxhsum /tmp/db.sql")
    return out.split()[0].decode()

def apply_operation():
    conn = psycopg2.connect("dbname='postgres' user='postgres' host='state' port=5432 password='postgres'")
    cur = conn.cursor()
    cur.execute('CREATE TABLE IF NOT EXISTS test (id bigserial PRIMARY KEY, data text NOT NULL);')
    cur.execute('INSERT INTO test(data) VALUES (\'123\');')
    conn.commit()
    conn.close()
    cur.close()

if __name__ == '__main__':
    request = json.loads(sys.argv[1])
    if request['command'] == 'execute':
        apply_operation()
        write_db_to_file()
        print(xxhash_db_file())

    elif request['command'] == 'apply':
        apply_operation()

        write_db_to_file()
        hash = xxhash_db_file()
        print(hash)
        if hash != request['result']:
            sys.exit(-1)

        print(json.dumps(True))

    elif request['command'] == 'init':
        sys.exit(0)
    else:
        sys.exit(-1)