For information on creating custom CROSSTOOL/Dockerfile's, please see the
following document from the RBE team:
https://g3doc.corp.google.com/devtools/foundry/g3doc/dev/windows/user_guide.md?cl=head#image

All the steps below can be found in the RBE user guide. The commands have
been replaced with values used by Android Studio.

# Steps for updating the image.

1. Create a Windows VM with Docker preinstalled.

```
INSTANCE_NAME="rbe-windows-toolchain"

gcloud compute instances "${INSTANCE_NAME}" \
  --project=android-studio-build \
  --zone="us-east1-b" \
  --machine-type=n1-standard-16 \
  --boot-disk-size=500GB \
  --image-project=windows-cloud \
  --image-family=windows-2019-core-for-containers \
  --scopes=https://www.googleapis.com/auth/cloud-platform
```

RDP into the machine. It is recommended to use a RDP client like Remmina, as
the web based RDP client can have performance problems. Do this by setting the
password using the GCP console, and getting the external IP address (GCP
console).

2. Create an empty directory, with the contents of the Dockerfile.

```
mkdir rbe
cd rbe
notepad Dockerfile.
```

Paste the contents of the Dockerfile, or use GCS.

3. Build the image.

```
docker build . -t rbe_windows_toolchain
```

Push the image to GCR:

```
docker tag rbe_windows_toolchain gcr.io/google.com/android-studio-alphasource/rbe-windows2019-as
'C:\Program Files (x86)\Google\Cloud SDK\cloud_env.bat'
gcloud auth configure-docker
docker push gcr.io/google.com/android-studio-alphasource/rbe-windows2019-as
```

The last command will print a line containing the container digest

```
latest: digest: sha256:7c6a3968e02612241a83f750a950d8f26783d167fcd77ecc029d992f5d950ef8 size: 6637
```

Use the digest to update the `container_image` property of the windows platform
in tools/base/bazel/platforms/BUILD


## Debugging Docker Containers

After a container has been created, it can be very useful to run commands
inspecting the container contents. For example, to verify what version of
Visual Studio is installed, run the following

```
docker run rbe_windows_toolchain dir "C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Tools\MSVC"
```
