# Kataja Compiler
Compiler for Kataja Programming Language

## How to use
First Download the latest Version ([here](https://github.com/KatajaLanguage/Compiler/tree/master/releases)) and run the jar ````java -jar KatajaCompiler-0.9.0.jar````. The Compiler starts a command prompt and waits for arguments.

Every Argument has to be a combination of the arguments described below (Note that the order of arguments is fixed, in the Order of the list below, every argument is optional).

- ``-q`` quits the application after the execution of the current commands
- ``-d`` enables debug information
- ``-o String`` set the output folder to the given path
- ``-dc String...`` decompiles the files or folders with the given paths
- ``-c String...`` compiles the files or folders with the given paths
- ``-e String`` executes the main method defined in that file or folder
- ``-i String`` sets the input to the given File

# !!! IMPORTANT !!!
Note that every time before writing in a folder, while compiling or decompiling, all files and folders that the folder contains will be deleted first.

### Example:

````-d -c src/test/kataja/HelloWorld.ktj -e src/test/kataja/HelloWorld.ktj````

This Command will enable debug, compile and execute the HelloWorld file in the test folder of the Compiler

````-q -dc out/src/test/kataja/HelloWorld.class````

This Command will decompile the previous compiled HelloWorld file and quit the application after the decompilation has finished
