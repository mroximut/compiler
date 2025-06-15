int main() {
    bool x = true;
    bool y = false;
    
    //return false || !x && !y;
    //bool z = x && y; 
    //bool z = (!x && !y) && (x && !y && !x && y);
    //bool z = x && y || (!x && !y) && (x && !y && !x && y);
    //bool z = false || !(x) && !(y) && (x && !y && !x && y);
    //bool z = false || true;
    //bool z = false || false;
    //bool z = false || true ;

    //return z;

    // if (!z) {
    //     return 0;
    // }

    // return 1;

   
    bool z = x && y || (!x && !y) && (x && !y && !x && y); 

    if (!z) {
        return 0;
    }

    return 1;
}

