# ADA Handle Resolver: docs site

Documentation site for the ADA Handle Resolver, built with [Astro](https://astro.build) and
[Starlight](https://starlight.astro.build). It is published to
https://cardano-foundation.github.io/cf-adahandle-resolver/ by `.github/workflows/docs.yaml`.

## Develop

```sh
npm install
npm run dev       # http://localhost:4321/cf-adahandle-resolver/
npm run build     # static output in dist/
```

## Layout

| Path                          | Contents                                                                  |
| ----------------------------- | ------------------------------------------------------------------------- |
| `src/pages/index.astro`       | Home page (Tailwind), styled after cardanofoundation.org                   |
| `src/content/docs/`           | Docs pages (Markdown / MDX)                                               |
| `src/styles/starlight.css`    | Cardano Foundation theme for the docs: Switzer, greyscale, violet accent |
| `src/styles/landing.css`      | The same theme as Tailwind tokens, for the home page                      |
| `src/components/`             | Header (CF logo + product name) and footer overrides                      |
| `public/images/`              | Cardano Foundation logos                                                  |
| `openapi/`                    | OpenAPI spec rendered by `starlight-openapi` under `/api/`                |

The theme is the one the CF Token Metadata Registry's site uses, so the Cardano Foundation's Starlight sites look
alike. Keep them in step when changing it.

## Pages kept in two places

`src/content/docs/reference/datums.md` is the site version of the repository's
`docs/handle-datums.md` (no H1 or table of contents, ADR links to GitHub). Change both together.

## Refreshing the OpenAPI spec

The API reference is generated from the spec the resolver serves at `/apidocs`. After changing the
API, start a resolver without the indexer and fetch the spec:

```sh
# from the repository root
./mvnw package -DskipTests
SPRING_ACTIVE_PROFILES=disable-indexer,h2 DB_URL=jdbc:h2:mem:spec java -jar target/cf-adahandle-resolver-*.jar

# in website/
npm run openapi:fetch
```
