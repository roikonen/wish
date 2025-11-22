# Wish Management

**Disclaimer:** `fi.roikonen.app` package content is quickly crafted by using all possible bad software development methods.

`fi.roikonen.app` is just for simulating running imaginary `fi.roikonen.domain` with `fi.roikonen.structure` from Scalable Modeling.

## Running the application
```bash
sbt run
```

## Create and view wishes
```bash
curl -i -X POST "http://localhost:8080/child/simo/wish/Orange"
curl -i -X POST "http://localhost:8080/child/simo/wish/Banana"
curl -i "http://localhost:8080/child/simo"
```

## Cancel wish
```bash
curl -i -X POST "http://localhost:8080/child/simo/wish/[WISH ID]]/cancel"
```

## Get updates for Present Logistics
```bash
curl -i "http://localhost:8080/events/wish_updates/0"
```

## Get updates for Naughty Children Management
```bash
curl -i "http://localhost:8080/events/naughtiness/0"
```

## Bonus: Get private events

This can be used e.g. for backup purposes
```bash
 curl -i "http://localhost:8080/events/private/0"
```