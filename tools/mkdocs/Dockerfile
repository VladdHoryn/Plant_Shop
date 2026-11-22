# MkDocs Material with every version pinned (Python is not installed on the developer machine, see CLAUDE.md).
#   docker build -t plantshop/mkdocs:1.6.1-material9.7.6 tools/mkdocs
#   ./scripts/docs-build.sh            strict build into site/
#   ./scripts/docs-build.sh serve      live preview on http://localhost:8000
FROM python:3.12.16-slim
# git: the git-revision-date-localized plugin reads the last commit date of every page
RUN apt-get update && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/* \
    && git config --system --add safe.directory '*'
COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir --disable-pip-version-check -r /tmp/requirements.txt
WORKDIR /docs
EXPOSE 8000
ENTRYPOINT ["mkdocs"]
