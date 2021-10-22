#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>
#include <assert.h>

//---------------Auxiliar functions----------------

/*The functions firstData, secondData, thirdData, fourthData and fifthData
set the data field that is going to be used by an instruction with the DATA opCode;
Until the fifthData function we assume that data = 28 (00011100) just to explain the functions more easily;

dataField = 000000 (the 2 right most bits are the 2 left most bits
of the data and the 4 left most bits are 0s)*/
unsigned char firstData (unsigned char data) {return (data & 192) >> 6;}

//dataField = 011100 (6 right most bits of the data)
unsigned char secondData (unsigned char data) {return data & 63;}

//dataField = 000111 (6 left most bits of the data)
unsigned char thirdData (unsigned char data) {return (data & 252) >> 2;}

/*dataField = 000001 (the 2 left most bits are the 2 right most bits of the data
and the 4 right most bits are the 4 left most bits of the data)*/
unsigned char fourthData (unsigned char data) {
  unsigned char dataField = (data & 3) << 4, dataFieldHelper = (data & 240) >> 4;
  dataField = dataField | dataFieldHelper;
  return dataField;
}

/*dataField = 110011 (the 4 left most bits are the 4 right most bits of the data
and the 2 right most bits are both 1 to set the opacity to the correct value)*/
unsigned char fifthData (unsigned char data) {
  unsigned char dataField = (data & 15) << 2;
  dataField = dataField | 3;
  return dataField;
}

/*Get the data from two instructions with the DATA opCode;
The 2 left most bits of the data are the 2 right most bits of the dataHigh
and the 6 right most bits of the data are the 6 bits of the dataLow;
dataHigh = 000000, dataLow = 011100;
data = 00011100 = 28*/
unsigned char getRedOrOthers (unsigned char dataHigh, unsigned char dataLow) {
  unsigned char data = (dataHigh & 3) << 6;
  data = data | (dataLow & 63);
  return data;
}

/*Get the data from two instructions with the DATA opCode;
The 6 left most bits of the data are the 6 bits of the dataHigh
and the 2 right most bits of the data are the 2 left most bits of the dataLow;
dataHigh = 000111, dataLow = 000001;
data = 00011100 = 28*/
unsigned char getGreen (unsigned char dataHigh, unsigned char dataLow) {
  unsigned char data = (dataHigh & 63) << 2;
  data = data | ((dataLow & 48) >> 4);
  return data;
}

/*Get the data from two instructions with the DATA opCode;
The 4 left most bits of the data are the 4 right most bits of the dataHigh
and the 4 right most bits of the data are the 4 left most bits of the dataLow;
dataHigh = 000001, dataLow = 110011;
data = 00011100 = 28*/
unsigned char getBlue (unsigned char dataHigh, unsigned char dataLow) {
  unsigned char data = (dataHigh & 15) << 4;
  data = data | ((dataLow & 60) >> 2);
  return data;
}

//------------------Core functions------------------

//Convert the file from .pgm to .sk
int pgmToSk (char *namePgm, char *nameSketch) {
  //Open the file that is going to be read
  FILE *pgmFile = fopen(namePgm, "rb");
  //If the file is not in the current directory, report and return
  if (pgmFile == NULL) {
    printf("Could not open the file specified.\n");
    return 1;
  }
  //If successful, create the new file that is going to be written
  FILE *new = fopen(nameSketch, "wb");
  /*Get the header from the basic PGM format ("P5 %d %d 255\n");
  16 is the correct number of characters in the header (2(P5) + 3(200) + 3(200) + 3(255) + 1(\n) + 3(spaces) + 1(\0))*/
  char header[16];
  fgets(header, 16, pgmFile);
  /*The basic PGM format file has colours between 0 and 255 (unsigned chars);
  intermediate is used to compare the current colour with the next one;
  wStart is the abscissa, starting in 0, where we start to draw in the current ordinate (inclusive);
  wFinal is the abscissa where we finish to draw in the current ordinate (exclusive);
  hStart is the ordinate, starting in 0, where we start to draw in the current abscissae (inclusive);
  hFinal is the ordinate where we finish to draw in the current abscissae (exclusive)*/
  unsigned char colour = fgetc (pgmFile), intermediate, width = 200, wStart = 0, wFinal = 1, height = 200, hStart = 0, hFinal = 1;
  //Read the file until it finishes
  while (!feof(pgmFile) && hFinal < height + 1) {
    /*When we want to change the colour, the data field should be: colour colour colour 255;
    In this part of the comments we assume again that colour = 28 (00011100);
    We need to have the data field set as 00011100 00011100 00011100 11111111;
    To do that we set the opCode to DATA several times*/
    for (int j = 0; j < 5; ++j) {
      //data = 000000
      if (j == 0) fputc(192+firstData(colour), new);
      //data = 000000 011100
      if (j == 1) fputc(192+secondData(colour), new);
      //data = 000000 011100 000111
      if (j == 2) fputc(192+thirdData(colour), new);
      //data = 000000 011100 000111 000001
      if (j == 3) fputc(192+fourthData(colour), new);
      //data = 000000 011100 000111 000001 110011
      if (j == 4) fputc(192+fifthData(colour), new);
    }
    //data = 00 011100 000111 000001 110011 111111
    fputc(255, new);
    //Now we can actually change the colour by seting the opCode to TOOL and the operand to COLOUR
    fputc(131, new);
    //data = wStart
    if (wStart < 64) fputc(192+wStart, new);
    else {
      fputc(192+firstData(wStart), new);
      fputc(192+secondData(wStart), new);
    }
    //tx = data = wStart
    fputc(132, new);
    //data = hStart
    if (hStart < 64) fputc(192+hStart, new);
    else {
      fputc(192+firstData(hStart), new);
      fputc(192+secondData(hStart), new);
    }
    //ty = data = hStart
    fputc(133, new);
    //Tool = NONE
    fputc(128, new);
    //We set the opCode to DY to set x = wStart and y = hStart
    fputc(64, new);
    intermediate = colour;
    colour = fgetc (pgmFile);
    //Check if consecutive pixels have the same colour and if we reached the last abscissa in the current ordinate
    while (colour == intermediate && wFinal < width) {
      ++wFinal;
      colour = fgetc (pgmFile);
    }
    //data = wFinal
    if (wFinal < 64) fputc(192+wFinal, new);
    else {
      fputc(192+firstData(wFinal), new);
      fputc(192+secondData(wFinal), new);
    }
    //tx = data = wFinal
    fputc(132, new);
    //data = hFinal
    if (hFinal < 64) fputc(192+hFinal, new);
    else {
      fputc(192+firstData(hFinal), new);
      fputc(192+secondData(hFinal), new);
    }
    //ty = data = hFinal
    fputc(133, new);
    //Tool = BLOCK
    fputc(130, new);
    /*We set the opCode to DY to draw;
    block(d, s->x, s->y, (s->tx) - (s->x), (s->ty) - (s->y));
    block(d, wStart, hStart, wFinal - wStart, hFinal - hStart)*/
    fputc(64, new);
    //If we reached the last abscissa in the current ordinate, we need to start from the 0th pixel in the next ordinate
    if (wFinal == width) {
      wStart = 0;
      wFinal = 1;
      ++hStart;
      ++hFinal;
    }
    //If not, we start from the next abscissa, the one that has not been drawn yet
    else {
      wStart = wFinal;
      ++wFinal;
    }
  }
  fclose(pgmFile);
  fclose(new);
  printf("File %s has been written.\n", nameSketch);
  return 0;
}

//Convert the file from .sk to .pgm
int skToPgm (char *namePgm, char *nameSketch) {
  //Open the file that is going to be read
  FILE *skFile = fopen(nameSketch, "rb");
  //If the file is not in the current directory, report and return
  if (skFile == NULL) {
    printf("Could not open the file specified.\n");
    return 1;
  }
  /*wStart is the abscissa, starting in 0, where we start to draw in the current ordinate (inclusive);
  wFinal is the abscissa where we finish to draw in the current ordinate (exclusive);
  h is the ordinate, starting in 0, where we draw in the current abscissae;
  countData is used to get the number of consecutive instructions with the opCode DATA;
  datas is a list of instructions with the opCode DATA;
  write enables, or not, writing;
  read enables, or not, reading*/
  unsigned char instruction = fgetc (skFile), width = 200, wStart, wFinal,
  height = 200, h = 0, countData = 0, red, green, blue, colour, image[height][width], datas[5], dataHigh, dataLow;
  bool write = false, read = true;
  //Read the file until it finishes
  while (!feof(skFile) && h < height) {
    //Check if the instruction has the opCode DATA and if we have less than 5 consecutive instructions with the DATA opCode
    if ((instruction & 192) == 192 && countData < 5) {
      //Store the first 2 instructions with the DATA opCode
      if (countData == 0) datas[0] = instruction;
      else if (countData == 1) datas[1] = instruction;
      else if (countData == 2) datas[2] = instruction;
      else if (countData == 3) datas[3] = instruction;
      else if (countData == 4) datas[4] = instruction;
      ++countData;
    }
    //In the 6th consecutive instruction with the DATA opCode we get the colour from the first 2 instructions
    else if ((instruction & 192) == 192 && countData == 5) {
      red = getRedOrOthers(datas[0], datas[1]);
      green = getGreen(datas[2], datas[3]);
      blue = getBlue(datas[3], datas[4]);
      //Taking into account the relative luminance
      colour = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
      countData = 0;
    }
    /*Check if the instruction has the opCode TOOL and the operand COLOUR;
    We use this just to find the instruction which we are looking for*/
    else if (instruction ==  131) {
      //Store the next two instructions
      instruction = fgetc (skFile);
      dataHigh = instruction;
      instruction = fgetc (skFile);
      dataLow = instruction;
      //Check if the second instruction has the DATA opCode
      if ((instruction & 192) == 192) wStart = getRedOrOthers(dataHigh, dataLow);
      else {
        wStart = dataHigh & 63;
        read = false;
      }
      countData = 0;
    }
    /*Check if the instruction has the opCode TOOL and the operand NONE;
    We use this just to find the instruction which we are looking for*/
    else if (instruction == 128) {
      //We skip one instruction
      instruction = fgetc (skFile);
      //Store the next two instructions
      instruction = fgetc (skFile);
      dataHigh = instruction;
      instruction = fgetc (skFile);
      dataLow = instruction;
      //Check if the second instruction has the DATA opCode
      if ((instruction & 192) == 192) wFinal = getRedOrOthers(dataHigh, dataLow);
      else {
        wFinal = dataHigh & 63;
        read = false;
      }
      countData = 0;
      write = true;
    }
    else countData = 0;
    //Only write if we have everything set as expected
    if (write == true) {
      for (int i = wStart; i < wFinal; ++i) image[h][i] = colour;
      //Go to the next ordinate if we reached the last abscissa
      if (wFinal == 200) ++h;
      write = false;
    }
    //Only read if we used the last instruction read
    if (read == true) instruction = fgetc (skFile);
    read = true;
  }
  //Create the new file that is going to be written
  FILE *new = fopen(namePgm, "wb");
  //Write the header in the new file
  fprintf(new, "P5 %d %d 255\n", width, height);
  //Write the colour of each pixel
  fwrite(image, 1, height*width, new);
  fclose(skFile);
  fclose(new);
  printf("File %s has been written.\n", namePgm);
  return 0;
}

//----------------------Tests-----------------------

//Check the firstData function
void firstDataTest () {
  assert(firstData(178) == 2);
  assert(firstData(126) == 1);
}

//Check the secondData function
void secondDataTest () {
  assert(secondData(178) == 50);
  assert(secondData(126) == 62);
}

//Check the thirdData function
void thirdDataTest () {
  assert(thirdData(178) == 44);
  assert(thirdData(126) == 31);
}

//Check the fourthData function
void fourthDataTest () {
  assert(fourthData(178) == 43);
  assert(fourthData(126) == 39);
}

//Check the fifthData function
void fifthDataTest () {
  assert(fifthData(178) == 11);
  assert(fifthData(126) == 59);
}

//Check the getRedOrOthers function
void getRedOrOthersTest () {
  assert(getRedOrOthers(2,50) == 178);
  assert(getRedOrOthers(1,62) == 126);
}

//Check the getGreen function
void getGreenTest () {
  assert(getGreen(44,32) == 178);
  assert(getGreen(31,32) == 126);
}

//Check the getBlue function
void getBlueTest () {
  assert(getBlue(11,8) == 178);
  assert(getBlue(7,56) == 126);
}

//----------------------Main------------------------

int main (int n, char *args[n]) {
  //Run the tests if no arguments are passed
  if (n == 1) {
    firstDataTest ();
    secondDataTest ();
    thirdDataTest ();
    fourthDataTest ();
    fifthDataTest ();
    getRedOrOthersTest ();
    getGreenTest ();
    getBlueTest ();
    printf("All tests pass!\n");
  }
  //Try to convert the file specified
  else if (n == 2) {
    int length = strlen(args[1]) + 1;
    char *name;
    name = strtok(args[1], ".");
    char *namePgm = malloc(2 * length * sizeof(char));
    sprintf(namePgm, "%s.pgm", name);
    char *nameSketch = malloc(2 * length * sizeof(char));
    sprintf(nameSketch, "%s.sk", name);
    name = strtok(NULL, ".");
    //Check if the argument has a dot
    if (name == NULL) printf("The file must have an extension.\n");
    //Check if the argument has a pgm extension
    else if (strcmp(name, "pgm") == 0) pgmToSk(namePgm, nameSketch);
    //Check if the argument has a sk extension
    else if (strcmp(name, "sk") == 0) skToPgm(namePgm, nameSketch);
    //The file has a different extension
    else printf("The file must be .sk or .pgm.\n");
    free(namePgm);
    free(nameSketch);
  }
  //More than one argument
  else printf("Write a maximum of one argument.\n");
  return 0;
}
