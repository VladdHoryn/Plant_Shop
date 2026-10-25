# Static-analysis runner with every version pinned: interpreter and analyzer. A metric may only change
# when the code changes, never because a runner image or a "latest" package moved underneath.
#   docker build -t plantshop/lizard:1.24.3-py3.13.17 tools/lizard
# Other versions (reliability experiment only):
#   docker build --build-arg PYTHON_VERSION=3.8.20 --build-arg LIZARD_VERSION=1.17.10 -t ... tools/lizard
ARG PYTHON_VERSION=3.13.17
FROM python:${PYTHON_VERSION}-slim
ARG LIZARD_VERSION=1.24.3
RUN pip install --no-cache-dir --disable-pip-version-check lizard==${LIZARD_VERSION}
WORKDIR /src
USER nobody
ENTRYPOINT ["lizard"]
