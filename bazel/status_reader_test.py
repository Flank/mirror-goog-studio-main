import os
import unittest
import status_reader


def read_file(path):
  with open(path) as f:
    return f.read()


def get_path(name):
  return os.path.join(os.getenv("TEST_TMPDIR"), name)


def create_file(name, content):
  path = get_path(name)
  with open(path, "w") as f:
    f.write(content)
  return path


class StatTest(unittest.TestCase):

  def test_stat(self):
    src = create_file("src.txt", "FOO 123\nBAR 456\nBAZ 789")
    dst = create_file("dst.txt", "")
    status_reader.main(["--src", src, "--dst", dst, "--key", "BAR"])
    self.assertEqual("456\n", read_file(dst))


if __name__ == "__main__":
  unittest.main()
