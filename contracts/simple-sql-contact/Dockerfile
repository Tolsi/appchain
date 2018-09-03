FROM python:alpine3.8
ADD contract.py /
ADD requirements.txt /
RUN apk update && apk add build-base postgresql-dev
RUN pip install -r requirements.txt
CMD [ "python", "./contract.py" ]
EXPOSE 5000
