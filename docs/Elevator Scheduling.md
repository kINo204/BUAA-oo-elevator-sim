# Elevator Scheduling

## info of an elevator

- current floor
- current load
- current direction
- current state: MOVING, CLOSING, OPENING
- next command
- next direction

## scheduling goal

1. responding time for single waiter
2. "total" running time(total in a situation of infinite request queue)

## regarding responding time

elevator to waiter:

1. moving towards
2. moving away from
3. idle

the strategy(in priority):

1. (idle) -> nearest -> less load
2. (moving towards) -> nearest -> less load
3. if all moving away: pass the request and record it

the scheduler:

look at current request
- if nonEmpty(towards OR idle): dispatch req to nearest elevator
- if empty(towards OR idle): record req

