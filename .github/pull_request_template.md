## What changes

One or two sentences.

## Documentation: docs-or-don't-merge (docs/developer/CONTRIBUTING.md, Definition of Done)

- [ ] `./scripts/doc-drift-check.sh`, `./scripts/docs-lint.sh` and `./scripts/docs-build.sh` pass locally
- [ ] New or changed endpoint / response format: `docs/architecture/sdd.md` section 5 updated; client path
      (catalog, orders, tracking): `docs/api/openapi.yaml` and its `info.version` (SemVer) updated
- [ ] `CHANGELOG.md` has an entry under `Unreleased`
- [ ] New dependency between services or new component: C4 diagram and component list in `docs/architecture/sdd.md` updated
- [ ] Deployment, startup order, variables, CI changed: `docs/architecture/isd.md` updated
- [ ] Architecture decision changed: new ADR in `docs/architecture/adr/`, the old one marked `Superseded`
- [ ] Front matter of touched pages: `owner`, `status`, `review_date` current; an outdated page is
      `status: deprecated` with `superseded_by`, never deleted
- [ ] New page: added to `nav` in `mkdocs.yml` and to `docs/lab-10/doc-map.txt`
- [ ] No documentation affected (explain why)
