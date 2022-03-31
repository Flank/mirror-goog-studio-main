# This file prints a Bazel workspace status, and is configured
# from a bazelrc file. See the Bazel manual for more:
# https://docs.bazel.build/versions/main/user-manual.html#workspace_status
#
# This is written in Python to be platform independent.
import getpass
import socket
import os

def getuser():
  """Gets the logged in user.

  Fallback to os.getlogin() since getpass on Windows may raise an exception.
  """
  try:
    return getpass.getuser()
  except:
    return os.getlogin()

print("BUILD_USERNAME %s" % getuser())
print("BUILD_HOSTNAME %s" % socket.gethostname())
