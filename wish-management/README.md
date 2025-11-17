```
sbt run
curl -i -X POST "http://localhost:8080/child/simo/I%20want%20an%20orange"
curl -i -X POST "http://localhost:8080/child/simo/I%20want%20a%20banana"
curl -i -X GET "http://localhost:8080/child/simo"
curl -i -X POST "http://localhost:8080/child/simo/wish/[WISH ID]]/cancel"
```