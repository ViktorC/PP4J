//============================================================================
// Name        : test.cpp
// Author      : Viktor Csomor
// Version     : 0.1
// Copyright   : -
// Description : A test program for the process pool.
//============================================================================

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <iostream>
#include <string>
#include <test.h>

using namespace std;

int main() {
	printf("hi\n");
	fflush(stdout);
	for (string line; getline(cin, line);) {
		int firstDelim = line.find(" ");
		string command = line.substr(0, firstDelim);
		if (command == "start") {
			wait(1);
			printf("ok\n");
		} else if (command == "process") {
			int sleepTime = atoi(line.substr(firstDelim + 1).c_str());
			if (sleepTime > 0) {
				wait(sleepTime);
				printf("ready\n");
				fflush(stdout);
			}
		} else if (command == "stop") {
			break;
		} else {
			printf("invalid command\n");
		}
		fflush(stdout);
	}
	printf("bye\n");
	fflush(stdout);
	return 0;
}
