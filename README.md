README.md, version 0.1

# Simple Lifter Control System

[TOC]

> made by: kino, 24/3/25

## Requirements

1. Introduction

2. Specifications

  - Elevator range: [1, 6]
  - Elevator working floors: [1, 11]
  - Initial floor: 0
  - Moving cost: 0.4s
  - Single door operation cost: 0.2
  - Maximum capacity: 6

3. I/O format

  - Input: [t]uid-FROM-beg-TO-dst-BY-eid
  - Output: 
    - [t]ARRIVE-flo-eid
    - [t]OPEN-flo-eid
    - [t]CLOSE-flo-eid
    - [t]IN-uid-flo-eid
    - [t]OUT-uid-flo-eid

## Mathematical Analysis of the Requirement

### A Discrete View

### A Continuous View

## Threads Management

### Mechanisms

**Concurrency lies everywhere.**

**What an operating system does?**

**Tradeoffs in our design: why not identical to OSs?** Our goals:

- Basic thread safety;
- High thread activity;
- Excellent performance with specified usage.

### Strategies
