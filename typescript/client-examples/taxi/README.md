This Taxi project is used by all the SDK examples to provide an Orbital server to run queries against.

You'll need Docker to run this. Once you have that installed, you can start the containers that power this project
by running `docker compose up -d` in a terminal. The documentation for Nebula (the thing that's powering the test ecosystem) can be [found here](https://nebula.orbitalhq.com/)

> [!NOTE] If running on Windows using WSL2, be sure to check this project out into a WSL location and run the docker compose command from within the WSL environment (ie. from within the Linux terminal).

## Queries to try from within Orbital

### Find
```taxiql
find { Film[] } as {
    name: Title
    id : FilmId
    description: Description
    }
    // Where can I watch this?
    platformName: StreamingProviderName
    price: PricerPerMonth
    // Grab some reviews
    rating: FilmReviewScore
    review: ReviewText
}[]
```

Click "Run query", and the query will be copied into the Query Editor ready for you to execute.

### Streaming query

```taxiql
stream {  NewFilmReleaseAnnouncement } as {
    announcement: NewFilmReleaseAnnouncement
    name: Title
    id : FilmId
    description: Description
    platformName: StreamingProviderName
    price: PricerPerMonth
    rating: FilmReviewScore
    review: ReviewText
}[]
```
