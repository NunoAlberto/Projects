// Basic program skeleton for a Sketch File (.sk) Viewer
#include "displayfull.h"
#include "sketch.h"
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>

// Allocate memory for a drawing state and initialise it
state *newState() {
  state *newState = malloc (sizeof(state));
  *newState = (state) {0, 0, 0, 0, LINE, 0, 0, 0};
  return newState;
}

// Release all memory associated with the drawing state
void freeState(state *s) {
  free(s);
}

// Extract an opcode from a byte (two most significant bits).
int getOpcode(byte b) {
  int opCode = b >> 6;
  return opCode;
}

// Extract an operand (-32..31) from the rightmost 6 bits of a byte.
int getOperand(byte b) {
  int operand;
  if ((b & 32) == 0) operand = b & 63;
  else operand = (b & 63) | -64;
  return operand;
}

// Execute the next byte of the command sequence.
void obey(display *d, state *s, byte op) {
  int opCode = getOpcode(op);
  int operand = getOperand(op);
  if (opCode == DX) s->tx += operand;
  else if (opCode == DY) {
    s->ty += operand;
    if (s->tool == LINE) line(d, s->x, s->y, s->tx, s->ty);
    else if (s->tool == BLOCK) block(d, s->x, s->y, (s->tx) - (s->x), (s->ty) - (s->y));
    s->x = s->tx;
    s->y = s->ty;
  }
  else if (opCode == TOOL) {
    if (operand == NONE) {
      s->tool = operand;
      s->data = 0;
    }
    if (operand == LINE) {
      s->tool = operand;
      s->data = 0;
    }
    if (operand == BLOCK) {
      s->tool = operand;
      s->data = 0;
    }
    if (operand == COLOUR) {
      colour(d, s->data);
      s->data = 0;
    }
    if (operand == TARGETX) {
      s->tx = s->data;
      s->data = 0;
    }
    if (operand == TARGETY) {
      s->ty = s->data;
      s->data = 0;
    }
    if (operand == SHOW) {
      show(d);
      s->data = 0;
    }
    if (operand == PAUSE) {
      pause(d, s->data);
      s->data = 0;
    }
    if (operand == NEXTFRAME) {
      s->end = true;
      s->data = 0;
    }
  }
  else if (opCode == DATA) s->data = (s->data << 6) | (operand & 63);
  s->start += 1;
}

// Draw a frame of the sketch file. For basic and intermediate sketch files
// this means drawing the full sketch whenever this function is called.
// For advanced sketch files this means drawing the current frame whenever
// this function is called.
bool processSketch(display *d, void *data, const char pressedKey) {
  if (data == NULL) return (pressedKey == 27);
  state *s = (state*) data;
  char *filename = getName(d);
  FILE *file = fopen (filename, "rb");
  fseek(file, s->start, SEEK_SET);
  unsigned char instruction = fgetc (file);
  while (!feof(file)) {
    obey(d, s, instruction);
    if (s->end == true) break;
    instruction = fgetc (file);
  }
  show(d);
  fclose(file);
  if (s->end == false) *s = (state) {0, 0, 0, 0, LINE, 0, 0, 0};
  else *s = (state) {0, 0, 0, 0, LINE, s->start, 0, 0};
  return (pressedKey == 27);
}

// View a sketch file in a 200x200 pixel window given the filename
void view(char *filename) {
  display *d = newDisplay(filename, 200, 200);
  state *s = newState();
  run(d, s, processSketch);
  freeState(s);
  freeDisplay(d);
}

// Include a main function only if we are not testing (make sketch),
// otherwise use the main function of the test.c file (make test).
#ifndef TESTING
int main(int n, char *args[n]) {
  if (n != 2) { // return usage hint if not exactly one argument
    printf("Use ./sketch file\n");
    exit(1);
  } else view(args[1]); // otherwise view sketch file in argument
  return 0;
}
#endif
