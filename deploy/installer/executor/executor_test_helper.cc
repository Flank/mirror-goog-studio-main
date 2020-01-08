#include <iostream>

int main(int argc, char* argv[]) {
  // Do the 6 combinations of using one pipe followed by using another one.
  // This is to make sure the caller is not expecting to read/write in any
  // specific order.
  int size = atoi(argv[1]);

  char* buffer = new char[size];
  // Initial read
  std::cin.read(buffer, size);
  // 1. in -> out
  std::cout.write(buffer, size);
  std::cout << std::endl;
  // 2. out -> err
  std::cerr.write(buffer, size);
  std::cerr << std::endl;
  // 3. err -> in
  std::cin.read(buffer, size);
  // 4. in -> err
  std::cerr.write(buffer, size);
  std::cerr << std::endl;
  // 5. err -> out
  std::cout.write(buffer, size);
  std::cout << std::endl;
  // 6. out -> in
  std::cin.read(buffer, size);
  // A final out to validate the last read
  std::cout.write(buffer, size);
  std::cout << std::endl;

  delete[] buffer;
}