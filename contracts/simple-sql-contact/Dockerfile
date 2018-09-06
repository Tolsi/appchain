FROM python:alpine3.8
ADD contract.py /
ADD requirements.txt /
ADD run.sh /
RUN apk update && apk add build-base postgresql-dev
RUN pip install -r requirements.txt
RUN apk del build-base
RUN chmod +x run.sh
CMD ["/bin/sleep", "6000"]
