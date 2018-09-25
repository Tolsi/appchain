FROM python:alpine3.8
ADD contract.py /
ADD requirements.txt /
ADD run.sh /
RUN apk update && apk add --no-cache build-base postgresql-dev postgresql-client xxhash
RUN pip install -r requirements.txt
RUN apk del build-base
RUN chmod +x run.sh
CMD ["/bin/sleep", "6000"]
