# Note: This file is for information purposes only. You can only build a windows
# docker image on a windows host. This command is here so developers can see how
# to run the command on a relevant Windows host.
gcloud --project google.com:android-studio-alphasource builds submit --tag gcr.io/google.com/android-studio-alphasource/rbe-windows2019-as docker_windows
