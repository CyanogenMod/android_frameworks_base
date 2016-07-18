#ifndef GENERATE_JAVA_H
#define GENERATE_JAVA_H

#include "aidl_language.h"
#include "AST.h"

#include <string>

using namespace std;

int generate_java(const string& filename, const string& originalSrc,
                interface_type* iface, int flags);

Class* generate_binder_interface_class(const interface_type* iface, int flags);
Class* generate_rpc_interface_class(const interface_type* iface);

string gather_comments(extra_text_type* extra);
string append(const char* a, const char* b);

class VariableFactory
{
public:
    VariableFactory(const string& base); // base must be short
    Variable* Get(Type* type);
    Variable* Get(int index);
private:
    vector<Variable*> m_vars;
    string m_base;
    int m_index;
};

//Set of flags that can be passed to generate_java
#define GENERATE_NO_OP_CLASS 1 << 0

#endif // GENERATE_JAVA_H

