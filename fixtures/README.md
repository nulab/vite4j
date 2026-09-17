# Fixtures

The manifests under `src/test/resources/manifests` come from real `vite build` runs,
not from hand-written JSON. A manifest carries shapes that are easy to forget when inventing one:
records for assets rather than chunks, records for a stylesheet on its own, and the split-out chunk
whose CSS an entry needs but does not list.

This directory is the project they are built from. It is deliberately small and has no framework in
it, so what it produces is about the bundler rather than about React or anything else.

It exercises the cases the library has to get right:

- two entries share a module, so the bundler splits it into a chunk of its own and its CSS goes with it
- two dynamic imports also use that module, so it is reachable both ways
- an asset large enough not to be inlined, so it gets a record of its own

## Regenerating

```sh
cd fixtures
npm install vite@8
npx vite build
cp dist/.vite/manifest.json ../src/test/resources/manifests/vite-8.json
```

Hashes in the filenames change when the sources do, so the test asserts on the shape and the
relationships rather than on exact names where it can.

Add a new file per major Vite version rather than replacing this one: the point is to notice when the
format moves.
