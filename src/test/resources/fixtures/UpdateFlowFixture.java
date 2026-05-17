class UpdateFlowFixture {
    void example() {
        Snap.snap(42).update().matches("""
                old content
                """, Object::toString);
    }
}
