# datamodeler-api

This project uses Quarkus, the Supersonic Subatomic Java Framework.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```
Then visit the Dev UI at http://localhost:8086/q/dev/

IMPORTANT:
As of right now (2026-01-30), you need quarkus version 3.30.
Using quarkus 3.31 will break certain endpoints.

## Docker based Deployment
### Relevant input data
The datamodeler currently supports deployment with external inputs. 

#### Optional: UMLS
The UMLS files `MRREL.RRF` and `MRCONSO.RRF` may be used to import the UMLS ontology nodes.

We suggest adding them via a docker volume:
```
- ./<path-to-your-local-umls>:/data/umls:ro
```

To start the import, please check the create method of the `UmlsService.java`.

#### Optional: Sapbert
