# appchain

Experiments on launching blockchain applications in docker containers

## How it works

1. Start the local docker registry
```
docker run -d -p 5000:5000 --name registry registry:2
```

2. deploy some contract template

```
cd contracts/hello-world-contract
docker build -t $(basename "$PWD") .
docker image tag $(basename "$PWD") localhost:5000/$(basename "$PWD")
docker push localhost:5000/$(basename "$PWD")
cd ..
```

3. execute it!
```
sbt run
```
