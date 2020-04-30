# How to build?

The project file is created with Visual Studio 2017. You will need C++ support in order to build it.

1. Make sure msbuild is in your path. For example you can use the "Developer Command Prompt for VS2017"

2. Run the following command

   ```cmd
   msbuild /p:Configuration=Release /p:Platform=x64
   ```

3. The generated file is stored in <solution_root>/x64/Release

