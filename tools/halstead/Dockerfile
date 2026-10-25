# Halstead analysis with every version pinned: interpreter, lexer (Pygments) and the cross-check tool multimetric.
#   docker build -t plantshop/halstead:1.0-py3.13.17 tools/halstead
#   docker run --rm -v "$PWD:/src:ro" plantshop/halstead:1.0-py3.13.17 [--vocabulary] <files...>
#   docker run --rm -v "$PWD:/src:ro" --entrypoint multimetric plantshop/halstead:1.0-py3.13.17 <files...>
ARG PYTHON_VERSION=3.13.17
FROM python:${PYTHON_VERSION}-slim
RUN pip install --no-cache-dir --disable-pip-version-check --root-user-action=ignore \
        pygments==2.21.0 multimetric==2.4.5
COPY halstead.py test_halstead.py /usr/local/bin/
# the image is only built when the hand-counted checks pass
RUN cd /usr/local/bin && python -m unittest -q test_halstead
WORKDIR /src
USER nobody
ENTRYPOINT ["python", "/usr/local/bin/halstead.py"]
