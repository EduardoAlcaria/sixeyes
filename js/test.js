const checkDiskSpace = require('check-disk-space').default;

let freeGB;
let totalGB


async function loadDiskSpace() {
  const { free, size } = await checkDiskSpace('D:')
  freeGB = free / (1024 ** 3)
  totalGB = size / (1024 ** 3) 
}

console.log(freeGB)
console.log(totalGB)