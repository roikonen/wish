# Wish Management

The aim for this project is demonstrate Scalable Modeling methodology.

**Disclaimer:** `fi.roikonen.app` package content is quickly crafted by using all possible bad software development methods. It is created just to run `fi.roikonen.domain` with `fi.roikonen.structure`.


## Running the application
```bash
sbt run
```

## Wish Management

### Send wishes
```bash
curl -i -X POST \
  "http://localhost:8080/child/simo/wish" \
  -H "Content-Type: application/json" \
  -d '{ "wish": "Knitted wool sock" }'
curl -i -X POST \
  "http://localhost:8080/child/simo/wish" \
  -H "Content-Type: application/json" \
  -d '{ "wish": "World peace" }'
```
### Send naughty wish
```bash
curl -i -X POST \
  "http://localhost:8080/child/simo/wish" \
  -H "Content-Type: application/json" \
  -d '{ "wish": "Flamethrower" }'
```

### Get child state
```bash
curl -s "http://localhost:8080/child/simo" | jq .
```

### Cancel wish
```bash
curl -i -X POST "http://localhost:8080/child/simo/wish/[WISH ID]/cancel"
```

## Integrations

### Outgoing Events

#### Get updates for Present Logistics
```bash
curl -s "http://localhost:8080/events/wish_updates/0" | jq .
```

#### Get updates for Naughty Children Management
```bash
curl -s "http://localhost:8080/events/naughtiness/0" | jq .
```

#### Bonus: Get private events

This can be used e.g. for backup purposes
```bash
 curl -s "http://localhost:8080/events/private/0" | jq .
```

### Incoming Commands

#### Mark child naughty for Naughty Children Management
```bash
curl -i -X POST \
  "http://localhost:8080/integration/child/simo/mark_naughty" \
  -H "Content-Type: application/json" \
  -d '{ "expirationTime": "2025-12-25T00:00:00.000Z" }'
```

#### Mark wish fulfilled for Present Logistics
```bash
curl -i -X POST "http://localhost:8080/integration/wish/[WISH ID]/mark_fulfilled"
```

## Journal (exposed in demonstration purposes)

### Streams
Journal consist of multiple streams. e.g. WishRejected-event goes to the following streams: 
* `wish`
* `child`
* `naughtiness`
  * if rejection reason was "naughty wish"
* `[WISH_ID]_wish`
* `simo_child`
* `[YYYY-MM-DD]_naughtiness`
  * if rejection reason was "naughty wish"

Streams feed projections like `Child.State`, `NaughtyList` and `Wish`.

```bash
 curl -s "http://localhost:8080/stream/simo_child/0" | jq .
```

## TODO List
-  [ ] Create `StateStore` and make `NaughtyList` state stored instead of event sourced.