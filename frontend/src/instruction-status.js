export function instructionStatusText(status) {
  switch (status) {
    case 'NONE':
      return '未生成'
    case 'PREVIEWED':
      return '已预览'
    case 'RELEASED':
      return '已释放'
    case 'PARTIALLY_RELEASED':
      return '部分释放'
    default:
      return status
  }
}

export function instructionTagType(status) {
  switch (status) {
    case 'RELEASED':
      return 'success'
    case 'PREVIEWED':
      return 'warning'
    case 'PARTIALLY_RELEASED':
      return 'warning'
    default:
      return 'info'
  }
}

export function directionText(direction) {
  return direction === 'PAY' ? '应付' : '应收'
}

export function directionTagType(direction) {
  return direction === 'PAY' ? 'danger' : 'success'
}
