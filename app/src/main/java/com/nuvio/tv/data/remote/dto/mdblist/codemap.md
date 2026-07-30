# app/src/main/java/com/nuvio/tv/data/remote/dto/mdblist/

## Responsibility

Models the small MDBList rating request and response surface.

## Design

`MDBListRatingRequestDto` carries an ID list and provider selector. The response wraps
nullable `MDBListRatingItemDto` values because a provider can return no rating. These are
plain data classes with explicit snake_case handling where needed.

## Flow

`MDBListRepository` resolves a usable IMDb ID, sends one request per enabled rating
provider through `MDBListApi`, and combines successful values into the domain ratings
result. Missing or failed provider values remain null.

## Integration

`MDBListApi` imports these types. `MDBListSettingsDataStore` controls whether the
repository runs and which providers are requested; `TmdbService` supplies ID conversion
when the source item is TMDB-only.
