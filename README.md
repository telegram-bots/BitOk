# BitNotify
Simple telegram bot. Notifies about changes in BitCoin transaction confirmations

## Args
| Description   | Type                   |
|---------------|------------------------|
| transactionId | HEX String (64 length) |
| untilN        | Int (0..100)           |
| everyN        | Int (0..100)           |

## Examples
#### Send notification once, when 2 confirmations is reached
`7169edc77f88250b8db005391ff5c7f88a09ee2e0fd66be6807b24a1ae3198b1`

#### Send notification once, when 5 confirmations is reached
`7169edc77f88250b8db005391ff5c7f88a09ee2e0fd66be6807b24a1ae3198b1 5`

#### Send notification for every third confirmation, until 9 confirmations is reached
`7169edc77f88250b8db005391ff5c7f88a09ee2e0fd66be6807b24a1ae3198b1 9 3`