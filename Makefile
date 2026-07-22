.PHONY: validate export tree archive

validate:
	./scripts/validate-blueprint.sh

export:
	./scripts/export-repositories.sh ./exported

tree:
	./scripts/project-tree.sh

archive:
	tar --exclude=.git --exclude=exported -czf private-registry-blueprint.tar.gz .
