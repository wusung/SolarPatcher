# asm-util
Submodule containing useful code for using ASM, and transforming classes. Used primarily for solar patcher, but can
be used as a library as well. The only dependency is ow2 ASM.  

The module contains 4 main features:
- Method and Field matching (mainly for the transformers)
- Extensions on asm-tree and reflection types
- Transformers (for methods, mostly)
- Other types of useful utility, like advice, ClassWriter with specific loaders etc.