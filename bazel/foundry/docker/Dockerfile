FROM gcr.io/cloud-marketplace/google/rbe-debian8@sha256:496193842f61c9494be68bd624e47c74d706cabf19a693c4653ffe96a97e43e3
RUN apt-get update && apt-get --no-install-recommends install -y ca-certificates-java && rm -rf /var/lib/apt/lists/*
# Masks /dev/random and makes it a directory, when java tries to use it it reverts to a different mechanism
VOLUME /dev/random
# Other failed attempts:
#RUN rm /dev/random && ln /dev/urandom /dev/random
#RUN mount --bind /dev/urandom /dev/random

