#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
int main(void) {
    const char *mode=getenv("LDY_TEST_CHILD");
    if(mode && !strcmp(mode,"exit"))return 1;
    puts("owned-child-test");fflush(stdout);
    for(;;)pause();
}
