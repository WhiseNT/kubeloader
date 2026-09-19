// ============================================
// 10 · 多级继承链 + 多态
// ============================================
// requires-transform
//
// 三级继承、super 链式往上找、父类方法里调用被子类覆盖的方法（多态）——
// 这些组合最容易暴露「只把方法挂到自己 prototype 上」这类半吊子实现。

class Shape {
    constructor(sides) {
        this.sides = sides
    }

    area() {
        return 0
    }

    describe() {
        return this.sides + '/' + this.area()
    }
}

class Square extends Shape {
    constructor(size) {
        super(4)
        this.size = size
    }

    area() {
        return this.size * this.size
    }
}

class UnitSquare extends Square {
    constructor() {
        super(1)
    }
}

class WhoChain {
    who() { return 'A' }
}

class Who2 extends WhoChain {
    who() { return 'B>' + super.who() }
}

class Who3 extends Who2 {
    who() { return 'C>' + super.who() }
}

__check('多态：父类方法调到子类实现', new Square(3).describe(), '4/9')
__check('三级：中间类构造器被调用', new UnitSquare().size, 1)
__check('三级：area 从父类继承', new UnitSquare().area(), 1)
__check('父类实例不受子类影响', new Shape(2).describe(), '2/0')
__check('super 链一路往上', new Who3().who(), 'C>B>A')
