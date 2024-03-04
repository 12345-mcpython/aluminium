from aluminium.enhance import Enhance

if __name__ == "__main__":
    a = Enhance.generate_random_enhance("hand", 1, 5)
    b = Enhance.generate_random_enhance("head", 1, 5)
    c = Enhance.generate_random_enhance("body", 1, 5)
    d = Enhance.generate_random_enhance("boot", 1, 5)
    e = Enhance.generate_random_enhance("ball", 1, 5)
    f = Enhance.generate_random_enhance("line", 1, 5)
    print(a.promote_level(114514))
    
