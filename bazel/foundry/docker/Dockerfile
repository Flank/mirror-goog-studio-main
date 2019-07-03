FROM gcr.io/cloud-marketplace/google/rbe-ubuntu16-04@sha256:94d7d8552902d228c32c8c148cc13f0effc2b4837757a6e95b73fdc5c5e4b07b
RUN apt-get update && apt-get --no-install-recommends install -y ca-certificates-java locales && rm -rf /var/lib/apt/lists/*
# Install lib32ncurses5 for sdk linker dependency on 32 bit libraries.
RUN apt-get update && apt-get install -y lib32ncurses5 lib32z1
# Container uses dockerUseURandom to mount /dev/urandom as /dev/random.
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8
RUN echo en_US.UTF-8 UTF-8 >> /etc/locale.gen
RUN locale-gen en_US.UTF-8
# For NDK bug (b/123781809)
ENV LD_LIBRARY_PATH ""
ENV CC ""
# Set a variable we can test to make sure we are using correct Docker Image
ENV STUDIO_IMAGE=true
